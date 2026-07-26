import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { cartApi } from "@/api/cart";
import { getErrorMessage } from "@/api/client";
import { useAuth } from "@/hooks/useAuth";
import type { CartItemAddRequest, CartItemUpdateRequest } from "@/types/api";

export const cartKeys = {
  all: ["cart"] as const,
};

export function useCart() {
  const { user } = useAuth();
  return useQuery({
    queryKey: cartKeys.all,
    queryFn: cartApi.get,
    enabled: !!user,
  });
}

export function useAddCartItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CartItemAddRequest) => cartApi.addItem(body),
    onSuccess: (data) => {
      queryClient.setQueryData(cartKeys.all, data);
      toast.success("Added to cart");
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}

export function useUpdateCartItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, body }: { productId: number; body: CartItemUpdateRequest }) =>
      cartApi.updateItem(productId, body),
    onSuccess: (data) => {
      queryClient.setQueryData(cartKeys.all, data);
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}

export function useRemoveCartItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productId: number) => cartApi.removeItem(productId),
    onSuccess: (data) => {
      queryClient.setQueryData(cartKeys.all, data);
      toast.success("Removed from cart");
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}

export function useClearCart() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => cartApi.clear(),
    onSuccess: (data) => {
      queryClient.setQueryData(cartKeys.all, data);
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}
