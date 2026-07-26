import { apiClient } from "@/api/client";
import type { CartItemAddRequest, CartItemUpdateRequest, CartResponse } from "@/types/api";

export const cartApi = {
  get: () => apiClient.get<CartResponse>("/cart").then((r) => r.data),

  addItem: (body: CartItemAddRequest) =>
    apiClient.post<CartResponse>("/cart/items", body).then((r) => r.data),

  updateItem: (productId: number, body: CartItemUpdateRequest) =>
    apiClient
      .put<CartResponse>(`/cart/items/${productId}`, body)
      .then((r) => r.data),

  removeItem: (productId: number) =>
    apiClient.delete<CartResponse>(`/cart/items/${productId}`).then((r) => r.data),

  clear: () => apiClient.delete<CartResponse>("/cart").then((r) => r.data),
};
