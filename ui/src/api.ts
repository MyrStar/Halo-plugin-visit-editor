import { axiosInstance } from "@halo-dev/api-client";

export interface PostVisitItem {
  name: string;
  title: string;
  permalink?: string;
  published: boolean;
  visit: number;
  publishTime?: string;
}

export interface PostVisitList {
  items: PostVisitItem[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

const base = "/apis/visiteditor.api.halo.run/v1alpha1";

export function listPostVisits(page: number, size: number, keyword?: string, sort?: string) {
  return axiosInstance.get<PostVisitList>(`${base}/posts`, {
    params: { page, size, keyword: keyword || undefined, sort: sort || undefined },
  });
}

export function updatePostVisit(name: string, visit: number) {
  return axiosInstance.put<PostVisitItem>(`${base}/posts/${name}/visit`, { visit });
}
