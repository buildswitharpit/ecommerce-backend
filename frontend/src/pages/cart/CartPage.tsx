import { Link, useNavigate } from "react-router-dom";
import { Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { useCart, useClearCart, useRemoveCartItem, useUpdateCartItem } from "@/hooks/use-cart";
import { formatCurrency } from "@/utils/format";
import { getErrorMessage } from "@/api/client";

export function CartPage() {
  const { data: cart, isLoading, isError, error, refetch } = useCart();
  const updateItem = useUpdateCartItem();
  const removeItem = useRemoveCartItem();
  const clearCart = useClearCart();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-64 w-full rounded-lg" />
      </div>
    );
  }

  if (isError) {
    return <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="Your cart is empty"
        description="Browse the catalog and add something you like."
        action={
          <Button asChild>
            <Link to="/products">Browse products</Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">Your Cart</h1>
        <Button variant="ghost" size="sm" onClick={() => clearCart.mutate()} disabled={clearCart.isPending}>
          Clear cart
        </Button>
      </div>

      <div className="divide-y rounded-lg border">
        {cart.items.map((item) => (
          <div key={item.id} className="flex items-center gap-4 p-4">
            <div className="flex-1">
              <Link to={`/products/${item.product.id}`} className="font-medium hover:underline">
                {item.product.name}
              </Link>
              <p className="text-sm text-muted-foreground">
                {formatCurrency(item.product.price)} each
              </p>
            </div>

            <div className="flex items-center rounded-md border">
              <Button
                variant="ghost"
                size="icon"
                disabled={item.quantity <= 1 || updateItem.isPending}
                onClick={() =>
                  updateItem.mutate({ productId: item.product.id, body: { quantity: item.quantity - 1 } })
                }
              >
                <Minus className="size-4" />
              </Button>
              <span className="w-8 text-center text-sm">{item.quantity}</span>
              <Button
                variant="ghost"
                size="icon"
                disabled={item.quantity >= item.product.stockQuantity || updateItem.isPending}
                onClick={() =>
                  updateItem.mutate({ productId: item.product.id, body: { quantity: item.quantity + 1 } })
                }
              >
                <Plus className="size-4" />
              </Button>
            </div>

            <span className="w-24 text-right font-medium">{formatCurrency(item.lineTotal)}</span>

            <Button
              variant="ghost"
              size="icon"
              onClick={() => removeItem.mutate(item.product.id)}
              disabled={removeItem.isPending}
            >
              <Trash2 className="size-4 text-destructive" />
            </Button>
          </div>
        ))}
      </div>

      <div className="flex items-center justify-between rounded-lg border p-4">
        <span className="text-lg font-medium">Total</span>
        <span className="text-lg font-semibold">{formatCurrency(cart.totalAmount)}</span>
      </div>

      <Button className="w-full" size="lg" onClick={() => navigate("/checkout")}>
        Proceed to checkout
      </Button>
    </div>
  );
}
