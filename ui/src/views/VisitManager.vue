<script setup lang="ts">
import {
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
  VTag,
} from "@halo-dev/components";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { listPostVisits, updatePostVisit, type PostVisitItem } from "../api";

const items = ref<PostVisitItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const keyword = ref("");
const sort = ref("publishTime,desc");
const loading = ref(false);
const saving = ref<string | null>(null);

const sortOptions = [
  { value: "publishTime,desc", label: "最新发布" },
  { value: "visit,desc", label: "阅读量从高到低" },
  { value: "visit,asc", label: "阅读量从低到高" },
];

// debounce search while typing
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(keyword, () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    page.value = 1;
    fetchData();
  }, 300);
});
watch(sort, () => {
  page.value = 1;
  fetchData();
});
onUnmounted(() => clearTimeout(searchTimer));

// draft values being edited, keyed by post name
const drafts = ref<Record<string, number>>({});

const dirty = (item: PostVisitItem) =>
  drafts.value[item.name] !== undefined && drafts.value[item.name] !== item.visit;

const hasDirty = computed(() => items.value.some((item) => dirty(item)));

async function fetchData() {
  loading.value = true;
  try {
    const { data } = await listPostVisits(page.value, size.value, keyword.value, sort.value);
    items.value = data.items;
    total.value = data.total;
    drafts.value = {};
  } catch (e) {
    console.error("Failed to fetch post visits", e);
    Toast.error("获取文章阅读量失败");
  } finally {
    loading.value = false;
  }
}

function onInput(item: PostVisitItem, event: Event) {
  const raw = (event.target as HTMLInputElement).value;
  const value = parseInt(raw, 10);
  drafts.value[item.name] = Number.isNaN(value) || value < 0 ? item.visit : value;
}

async function onSave(item: PostVisitItem) {
  const value = drafts.value[item.name];
  if (value === undefined || value === item.visit) return;
  saving.value = item.name;
  try {
    const { data } = await updatePostVisit(item.name, value);
    item.visit = data.visit;
    delete drafts.value[item.name];
    Toast.success(`「${item.title}」阅读量已更新为 ${data.visit}`);
  } catch (e) {
    console.error("Failed to update visit", e);
    Toast.error("更新失败，请稍后重试");
  } finally {
    saving.value = null;
  }
}

async function onSaveAll() {
  for (const item of items.value) {
    if (dirty(item)) {
      await onSave(item);
    }
  }
}

function formatTime(time?: string) {
  if (!time) return "-";
  return new Date(time).toLocaleString();
}

onMounted(fetchData);
</script>

<template>
  <VPageHeader title="阅读量管理">
    <template #actions>
      <VSpace>
        <VButton type="secondary" :disabled="!hasDirty" @click="onSaveAll">
          保存全部修改
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="visit-manager">
    <VCard>
      <div class="toolbar">
        <div class="search">
          <input
            v-model="keyword"
            type="search"
            class="search-input"
            placeholder="搜索文章标题，输入即搜"
          />
          <select v-model="sort" class="sort-select">
            <option v-for="opt in sortOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
        <span class="hint">共 {{ total }} 篇文章</span>
      </div>

      <VLoading v-if="loading" />

      <VEmpty
        v-else-if="items.length === 0"
        message="没有找到文章"
        title="暂无文章"
      />

      <table v-else class="post-table">
        <thead>
          <tr>
            <th>标题</th>
            <th class="col-status">状态</th>
            <th class="col-time">发布时间</th>
            <th class="col-visit">阅读量</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.name">
            <td>
              <a
                v-if="item.permalink"
                :href="item.permalink"
                target="_blank"
                class="post-title"
              >
                {{ item.title }}
              </a>
              <span v-else class="post-title">{{ item.title }}</span>
            </td>
            <td class="col-status">
              <VTag :theme="item.published ? 'default' : 'warning'">
                {{ item.published ? "已发布" : "未发布" }}
              </VTag>
            </td>
            <td class="col-time">{{ formatTime(item.publishTime) }}</td>
            <td class="col-visit">
              <input
                type="number"
                min="0"
                class="visit-input"
                :value="drafts[item.name] ?? item.visit"
                @input="onInput(item, $event)"
              />
            </td>
            <td class="col-action">
              <VButton
                size="sm"
                type="primary"
                :disabled="!dirty(item)"
                :loading="saving === item.name"
                @click="onSave(item)"
              >
                保存
              </VButton>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <VPagination
          v-model:page="page"
          v-model:size="size"
          :total="total"
          :size-options="[20, 50, 100]"
          @change="fetchData"
        />
      </div>
    </VCard>
  </div>
</template>

<style scoped>
.visit-manager {
  padding: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.search {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-input {
  width: 260px;
  padding: 7px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.search-input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.sort-select {
  padding: 7px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  background: #fff;
  cursor: pointer;
}

.sort-select:focus {
  outline: none;
  border-color: #2563eb;
}

.hint {
  color: #6b7280;
  font-size: 13px;
}

.post-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.post-table th,
.post-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.post-table th {
  color: #6b7280;
  font-weight: 500;
  background: #f9fafb;
}

.post-title {
  color: #111827;
  text-decoration: none;
}

a.post-title:hover {
  color: #2563eb;
}

.col-status {
  width: 90px;
}

.col-time {
  width: 170px;
}

.col-visit {
  width: 140px;
}

.col-action {
  width: 90px;
}

.visit-input {
  width: 110px;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.visit-input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
