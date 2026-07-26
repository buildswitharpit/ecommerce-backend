import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/ErrorState";
import { OrderStatusBadge } from "@/components/StatusBadge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useOrder, useUpdateOrderStatus } from "@/hooks/use-orders";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency, formatDateTime } from "@/utils/format";
import { getErrorMessage } from "@/api/client";
import type { OrderStatus } from "@/types/api";

const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ["CANCELLED"],
  PAID: ["SHIPPED", "CANCELLED"],
  PAYMENT_FAILED: ["CANCELLED"],
  SHIPPED: ["DELIVERED", "CANCELLED"],
  DELIVERED: [],
  CANCELLED: [],
};

export function OrderDetailPage() {
  const { id } = useParams();
  const orderId = Number(id);
  const navigate = useNavigate();
  const { user } = useAuth();
  const { data: order, isLoading, isError, error, refetch } = useOrder(orderId);
  const updateStatus = useUpdateOrderStatus();
  const [pendingStatus, setPendingStatus] = useState<OrderStatus | "">("");

  if (isLoading) return <Skeleton className="h-96 w-full rounded-lg" />;
  if (isError || !order) {
    return (
      <ErrorState
        message={error ? getErrorMessage(error) : "Order not found."}
        onRetry={() => refetch()}
      />
    );
  }

  const availableTransitions = NEXT_STATUSES[order.status];

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Button variant="ghost" size="sm" onClick={() => navigate(-1)} className="gap-1 px-0">
        <ArrowLeft className="size-4" />
        Back
      </Button>

      <div className="space-y-1">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">Order #{order.id}</h1>
          <OrderStatusBadge status={order.status} />
        </div>
        <p className="text-sm text-muted-foreground">Placed {formatDateTime(order.createdAt)}</p>
      </div>

      <div className="rounded-lg border">
        <div className="divide-y">
          {order.items.map((item) => (
            <div key={item.id} className="flex items-center justify-between p-4">
              <div>
                <p className="font-medium">{item.productName}</p>
                <p className="text-sm text-muted-foreground">
                  {formatCurrency(item.unitPrice)} &times; {item.quantity}
                </p>
              </div>
              <span className="font-medium">{formatCurrency(item.lineTotal)}</span>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-between border-t p-4 font-semibold">
          <span>Total</span>
          <span>{formatCurrency(order.totalAmount)}</span>
        </div>
      </div>

      {order.payment && (
        <div className="rounded-lg border p-4">
          <h2 className="mb-2 font-medium">Payment</h2>
          <dl className="grid grid-cols-2 gap-y-1 text-sm">
            <dt className="text-muted-foreground">Gateway</dt>
            <dd>{order.payment.gateway}</dd>
            <dt className="text-muted-foreground">Status</dt>
            <dd>{order.payment.status}</dd>
            <dt className="text-muted-foreground">Reference</dt>
            <dd className="truncate font-mono text-xs">{order.payment.gatewayReferenceId}</dd>
            <dt className="text-muted-foreground">Amount</dt>
            <dd>{formatCurrency(order.payment.amount)}</dd>
          </dl>
        </div>
      )}

      {user?.role === "ADMIN" && availableTransitions.length > 0 && (
        <div className="flex items-center gap-3 rounded-lg border p-4">
          <span className="text-sm font-medium">Update status:</span>
          <Select value={pendingStatus} onValueChange={(v) => setPendingStatus(v as OrderStatus)}>
            <SelectTrigger className="w-48">
              <SelectValue placeholder="Select status" />
            </SelectTrigger>
            <SelectContent>
              {availableTransitions.map((status) => (
                <SelectItem key={status} value={status}>
                  {status}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            size="sm"
            disabled={!pendingStatus || updateStatus.isPending}
            onClick={() => {
              if (pendingStatus) {
                updateStatus.mutate({ id: order.id, body: { status: pendingStatus } });
                setPendingStatus("");
              }
            }}
          >
            Apply
          </Button>
        </div>
      )}
    </div>
  );
}
