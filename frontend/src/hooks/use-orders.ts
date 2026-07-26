import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { getErrorMessage } from "@/api/client";
import { ordersApi, type AdminOrderListParams, type OrderListParams } from "@/api/orders";
import { cartKeys } from "@/hooks/use-cart";
import type { CheckoutRequest, OrderStatusUpdateRequest } from "@/types/api";

export const orderKeys = {
  all: ["orders"] as const,
  mine: (params: OrderListParams) => ["orders", "mine", params] as const,
  detail: (id: number) => ["orders", "detail", id] as const,
  admin: (params: AdminOrderListParams) => ["orders", "admin", params] as const,
};

export function useMyOrders(params: OrderListParams) {
  return useQuery({
    queryKey: orderKeys.mine(params),
    queryFn: () => ordersApi.myOrders(params),
    placeholderData: (prev) => prev,
  });
}

export function useOrder(id: number) {
  return useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: () => ordersApi.getById(id),
    enabled: Number.isFinite(id),
  });
}

export function useAdminOrders(params: AdminOrderListParams) {
  return useQuery({
    queryKey: orderKeys.admin(params),
    queryFn: () => ordersApi.adminListAll(params),
    placeholderData: (prev) => prev,
  });
}

export function useCheckout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CheckoutRequest) => ordersApi.checkout(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}

export function useUpdateOrderStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: OrderStatusUpdateRequest }) =>
      ordersApi.updateStatus(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      toast.success("Order status updated");
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}
