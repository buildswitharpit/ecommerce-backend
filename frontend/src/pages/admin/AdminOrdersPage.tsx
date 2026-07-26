import { useState } from "react";
import { Link } from "react-router-dom";
import { Package } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { EmptyState } from "@/components/EmptyState";
import { ErrorState } from "@/components/ErrorState";
import { PageControls } from "@/components/PageControls";
import { OrderStatusBadge } from "@/components/StatusBadge";
import { useAdminOrders } from "@/hooks/use-orders";
import { formatCurrency, formatDateTime } from "@/utils/format";
import { getErrorMessage } from "@/api/client";
import type { OrderStatus } from "@/types/api";

const PAGE_SIZE = 10;
const ALL_STATUSES: OrderStatus[] = [
  "PENDING",
  "PAID",
  "PAYMENT_FAILED",
  "SHIPPED",
  "DELIVERED",
  "CANCELLED",
];

export function AdminOrdersPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<OrderStatus | "ALL">("ALL");

  const { data, isLoading, isError, error, refetch } = useAdminOrders({
    page,
    size: PAGE_SIZE,
    sort: "createdAt,desc",
    status: status === "ALL" ? undefined : status,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">All Orders</h1>
        <p className="text-muted-foreground">Cross-customer order visibility (admin-only).</p>
      </div>

      <Select
        value={status}
        onValueChange={(v) => {
          setStatus(v as OrderStatus | "ALL");
          setPage(0);
        }}
      >
        <SelectTrigger className="w-56">
          <SelectValue placeholder="Filter by status" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">All statuses</SelectItem>
          {ALL_STATUSES.map((s) => (
            <SelectItem key={s} value={s}>
              {s}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {isLoading && <Skeleton className="h-64 w-full rounded-lg" />}
      {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

      {!isLoading && !isError && data && data.content.length === 0 && (
        <EmptyState icon={Package} title="No orders found" description="No orders match this filter." />
      )}

      {!isLoading && !isError && data && data.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Order</TableHead>
                  <TableHead>Placed</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                  <TableHead className="text-right">Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell className="font-medium">#{order.id}</TableCell>
                    <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                    <TableCell>
                      <OrderStatusBadge status={order.status} />
                    </TableCell>
                    <TableCell className="text-right">{formatCurrency(order.totalAmount)}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="sm" asChild>
                        <Link to={`/orders/${order.id}`}>View</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <PageControls page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
