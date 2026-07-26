import { Badge } from "@/components/ui/badge";
import type { OrderStatus } from "@/types/api";
import { cn } from "@/lib/utils";

const STATUS_STYLES: Record<OrderStatus, string> = {
  PENDING: "bg-muted text-muted-foreground",
  PAID: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  PAYMENT_FAILED: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
  SHIPPED: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300",
  DELIVERED: "bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300",
  CANCELLED: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Badge className={cn("font-medium", STATUS_STYLES[status])} variant="outline">
      {status.replace("_", " ")}
    </Badge>
  );
}
