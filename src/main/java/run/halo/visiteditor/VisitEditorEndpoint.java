package run.halo.visiteditor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Counter;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/**
 * 文章阅读量管理 API。
 *
 * <p>阅读量存储在 metrics.halo.run/v1alpha1 的 Counter 扩展中，
 * 名称为 {@code posts.content.halo.run/{文章名}}。修改该扩展后，
 * Halo 的 PostCounterReconciler 会自动将最新统计同步到文章的 stats 注解上。
 */
@Component
@RequiredArgsConstructor
public class VisitEditorEndpoint implements CustomEndpoint {

    static final String GROUP = "visiteditor.api.halo.run";
    static final String VERSION = "v1alpha1";

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(GROUP, VERSION);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = GROUP + "/" + VERSION + "/PostVisit";
        return SpringdocRouteBuilder.route()
            .GET("posts", this::listPostVisits, builder -> builder
                .operationId("ListPostVisits")
                .description("List posts with visit counts.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("page").in(ParameterIn.QUERY).implementation(Integer.class))
                .parameter(parameterBuilder()
                    .name("size").in(ParameterIn.QUERY).implementation(Integer.class))
                .parameter(parameterBuilder()
                    .name("keyword").in(ParameterIn.QUERY).implementation(String.class))
                .response(responseBuilder().implementation(PostVisitList.class)))
            .PUT("posts/{name}/visit", this::updateVisit, builder -> builder
                .operationId("UpdatePostVisit")
                .description("Set the visit count of a post.")
                .tag(tag)
                .parameter(parameterBuilder()
                    .name("name").in(ParameterIn.PATH).required(true)
                    .implementation(String.class))
                .requestBody(requestBodyBuilder()
                    .required(true)
                    .content(contentBuilder()
                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(UpdateVisitRequest.class))))
                .response(responseBuilder().implementation(PostVisitItem.class)))
            .build();
    }

    /**
     * Counter extension name for a post, same as
     * {@code MeterUtils.nameOf(Post.class, postName)} in Halo core.
     */
    static String counterNameOf(String postName) {
        return "posts.content.halo.run/" + postName;
    }

    private Mono<ServerResponse> listPostVisits(ServerRequest request) {
        final int page = request.queryParam("page").map(Integer::parseInt).orElse(1);
        final int size = request.queryParam("size").map(Integer::parseInt).orElse(20);
        final String keyword = request.queryParam("keyword").orElse(null);

        return client.listAll(Post.class, ListOptions.builder().build(), ExtensionUtil.defaultSort())
            .filter(post -> post.getMetadata().getDeletionTimestamp() == null)
            .filter(post -> StringUtils.isBlank(keyword)
                || StringUtils.containsIgnoreCase(post.getSpec().getTitle(), keyword))
            .collectList()
            .flatMap(posts -> {
                var total = posts.size();
                var from = Math.min(Math.max(page - 1, 0) * size, total);
                var to = Math.min(from + size, total);
                var paged = posts.subList(from, to);

                var counterNames = paged.stream()
                    .map(post -> counterNameOf(post.getMetadata().getName()))
                    .toList();
                var options = ListOptions.builder()
                    .andQuery(Queries.in("metadata.name", counterNames))
                    .build();

                Mono<Map<String, Integer>> visitMapMono = counterNames.isEmpty()
                    ? Mono.just(Map.of())
                    : client.listAll(Counter.class, options, ExtensionUtil.defaultSort())
                        .collectMap(counter -> counter.getMetadata().getName(),
                            counter -> counter.getVisit() == null ? 0 : counter.getVisit());

                return visitMapMono.map(visitMap -> {
                    var items = paged.stream()
                        .map(post -> toItem(post,
                            visitMap.getOrDefault(counterNameOf(post.getMetadata().getName()), 0)))
                        .toList();
                    return new PostVisitList(items, total, page, size,
                        to < total, from > 0);
                });
            })
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> updateVisit(ServerRequest request) {
        var postName = request.pathVariable("name");
        return request.bodyToMono(UpdateVisitRequest.class)
            .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("Request body must not be empty")))
            .flatMap(body -> {
                if (body.visit() == null || body.visit() < 0) {
                    return Mono.error(
                        () -> new IllegalArgumentException("Visit must be a non-negative integer"));
                }
                return client.fetch(Post.class, postName)
                    .switchIfEmpty(Mono.error(
                        () -> new IllegalArgumentException("Post not found: " + postName)))
                    .flatMap(post -> setVisit(postName, body.visit())
                        .thenReturn(toItem(post, body.visit())));
            })
            .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<Counter> setVisit(String postName, int visit) {
        var counterName = counterNameOf(postName);
        return client.fetch(Counter.class, counterName)
            .flatMap(counter -> {
                counter.setVisit(visit);
                return client.update(counter);
            })
            .switchIfEmpty(Mono.defer(() -> {
                var counter = Counter.emptyCounter(counterName);
                counter.setVisit(visit);
                return client.create(counter);
            }));
    }

    static PostVisitItem toItem(Post post, int visit) {
        var permalink = post.getStatus() == null ? null : post.getStatus().getPermalink();
        return new PostVisitItem(
            post.getMetadata().getName(),
            post.getSpec().getTitle(),
            permalink,
            Boolean.TRUE.equals(post.getSpec().getPublish()),
            visit,
            post.getSpec().getPublishTime());
    }

    public record UpdateVisitRequest(@Schema(requiredMode = REQUIRED) Integer visit) {
    }

    public record PostVisitItem(String name, String title, String permalink, boolean published,
                                int visit, Instant publishTime) {
    }

    public record PostVisitList(List<PostVisitItem> items, long total, int page, int size,
                                boolean hasNext, boolean hasPrevious) {
    }
}
