import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/ErrorState";
import { useProduct } from "@/hooks/use-products";
import { useAddCartItem } from "@/hooks/use-cart";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency } from "@/utils/format";
import { getErrorMessage } from "@/api/client";

export function ProductDetailPage() {
  const { id } = useParams();
  const productId = Number(id);
  const navigate = useNavigate();
  const { user } = useAuth();
  const [quantity, setQuantity] = useState(1);

  const { data: product, isLoading, isError, error, refetch } = useProduct(productId);
  const addItem = useAddCartItem();

  if (isLoading) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-64 w-full rounded-lg" />
      </div>
    );
  }

  if (isError || !product) {
    return (
      <ErrorState
        message={error ? getErrorMessage(error) : "Product not found."}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="gap-1 px-0">
        <ArrowLeft className="size-4" />
        Back
      </Button>

      <div className="space-y-3 rounded-lg border p-6">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h1 className="text-2xl font-semibold">{product.name}</h1>
            <p className="text-sm text-muted-foreground">SKU: {product.sku}</p>
          </div>
          {product.category && <Badge variant="secondary">{product.category}</Badge>}
        </div>

        <p className="text-muted-foreground">
          {product.description || "No description available."}
        </p>

        <div className="flex items-center justify-between border-t pt-4">
          <span className="text-3xl font-bold">{formatCurrency(product.price)}</span>
          <span className="text-sm text-muted-foreground">
            {product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : "Out of stock"}
          </span>
        </div>

        {user ? (
          <div className="flex items-center gap-3 pt-2">
            <div className="flex items-center rounded-md border">
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                disabled={quantity <= 1}
              >
                <Minus className="size-4" />
              </Button>
              <span className="w-8 text-center text-sm">{quantity}</span>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setQuantity((q) => Math.min(product.stockQuantity, q + 1))}
                disabled={quantity >= product.stockQuantity}
              >
                <Plus className="size-4" />
              </Button>
            </div>
            <Button
              className="flex-1"
              disabled={product.stockQuantity === 0 || addItem.isPending}
              onClick={() => addItem.mutate({ productId: product.id, quantity })}
            >
              Add to cart
            </Button>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            <Link to="/login" className="underline underline-offset-4">
              Log in
            </Link>{" "}
            to add this product to your cart.
          </p>
        )}
      </div>
    </div>
  );
}
