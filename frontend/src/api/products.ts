import { apiClient } from "@/api/client";
import type {
  Page,
  ProductCreateRequest,
  ProductResponse,
  ProductUpdateRequest,
  StockAdjustRequest,
} from "@/types/api";

export interface ProductListParams {
  category?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const productsApi = {
  list: (params: ProductListParams) =>
    apiClient
      .get<Page<ProductResponse>>("/products", { params })
      .then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<ProductResponse>(`/products/${id}`).then((r) => r.data),

  create: (body: ProductCreateRequest) =>
    apiClient.post<ProductResponse>("/products", body).then((r) => r.data),

  update: (id: number, body: ProductUpdateRequest) =>
    apiClient.put<ProductResponse>(`/products/${id}`, body).then((r) => r.data),

  remove: (id: number) => apiClient.delete<void>(`/products/${id}`),

  adjustStock: (id: number, body: StockAdjustRequest) =>
    apiClient
      .patch<ProductResponse>(`/products/${id}/stock`, body)
      .then((r) => r.data),
};
