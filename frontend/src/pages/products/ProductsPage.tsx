import { useState } from "react";
import { Link } from "react-router-dom";
import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { PageControls } from "@/components/PageControls";
import { useProducts } from "@/hooks/use-products";
import { useAddCartItem } from "@/hooks/use-cart";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency } from "@/utils/format";
import { getErrorMessage } from "@/api/client";

const PAGE_SIZE = 12;

export function ProductsPage() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, error, refetch } = useProducts({
    search: search || undefined,
    category: category || undefined,
    page,
    size: PAGE_SIZE,
  });
  const { user } = useAuth();
  const addItem = useAddCartItem();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
        <p className="text-muted-foreground">Browse the catalog and add items to your cart.</p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search products..."
            className="pl-9"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <Input
          placeholder="Filter by category..."
          className="sm:max-w-56"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value);
            setPage(0);
          }}
        />
      </div>

      {isLoading && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-56 rounded-lg" />
          ))}
        </div>
      )}

      {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

      {!isLoading && !isError && data && data.content.length === 0 && (
        <EmptyState
          title="No products found"
          description="Try a different search term or clear the filters."
        />
      )}

      {!isLoading && !isError && data && data.content.length > 0 && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((product) => (
              <Card key={product.id} className="flex flex-col">
                <CardHeader>
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base">
                      <Link to={`/products/${product.id}`} className="hover:underline">
                        {product.name}
                      </Link>
                    </CardTitle>
                    {product.category && <Badge variant="secondary">{product.category}</Badge>}
                  </div>
                </CardHeader>
                <CardContent className="flex-1 space-y-2">
                  <p className="line-clamp-2 text-sm text-muted-foreground">
                    {product.description || "No description available."}
                  </p>
                  <p className="text-lg font-semibold">{formatCurrency(product.price)}</p>
                  <p className="text-xs text-muted-foreground">
                    {product.stockQuantity > 0
                      ? `${product.stockQuantity} in stock`
                      : "Out of stock"}
                  </p>
                </CardContent>
                <CardFooter className="gap-2">
                  <Button variant="outline" size="sm" className="flex-1" asChild>
                    <Link to={`/products/${product.id}`}>Details</Link>
                  </Button>
                  {user && (
                    <Button
                      size="sm"
                      className="flex-1"
                      disabled={product.stockQuantity === 0 || addItem.isPending}
                      onClick={() => addItem.mutate({ productId: product.id, quantity: 1 })}
                    >
                      Add to cart
                    </Button>
                  )}
                </CardFooter>
              </Card>
            ))}
          </div>
          <PageControls page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
