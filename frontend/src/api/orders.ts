import { apiClient } from "@/api/client";
import type {
  CheckoutRequest,
  OrderResponse,
  OrderStatus,
  OrderStatusUpdateRequest,
  Page,
} from "@/types/api";

export interface OrderListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdminOrderListParams extends OrderListParams {
  status?: OrderStatus;
}

export const ordersApi = {
  checkout: (body: CheckoutRequest) =>
    apiClient.post<OrderResponse>("/orders/checkout", body).then((r) => r.data),

  myOrders: (params: OrderListParams) =>
    apiClient.get<Page<OrderResponse>>("/orders", { params }).then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<OrderResponse>(`/orders/${id}`).then((r) => r.data),

  updateStatus: (id: number, body: OrderStatusUpdateRequest) =>
    apiClient
      .patch<OrderResponse>(`/orders/${id}/status`, body)
      .then((r) => r.data),

  adminListAll: (params: AdminOrderListParams) =>
    apiClient
      .get<Page<OrderResponse>>("/admin/orders", { params })
      .then((r) => r.data),
};
