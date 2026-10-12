import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import TablerChartBar from "~icons/tabler/chart-bar";
import VisitManager from "./views/VisitManager.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/visit-editor",
        name: "VisitEditor",
        component: VisitManager,
        meta: {
          title: "阅读量管理",
          permissions: ["plugin:visit-editor:manage"],
          menu: {
            name: "阅读量管理",
            group: "content",
            icon: markRaw(TablerChartBar),
          },
        },
      },
    },
  ],
  extensionPoints: {},
});
