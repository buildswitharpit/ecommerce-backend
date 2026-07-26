import { useEffect } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAdjustStock } from "@/hooks/use-products";
import type { InventoryReason, ProductResponse } from "@/types/api";

const stockSchema = z.object({
  quantityChange: z.coerce.number().int().refine((n) => n !== 0, "Enter a non-zero amount"),
  reason: z.enum(["RESTOCK", "ORDER_CANCELLED", "ORDER_PLACED"] as [InventoryReason, ...InventoryReason[]]),
});

type StockFormInput = z.input<typeof stockSchema>;
type StockForm = z.output<typeof stockSchema>;

interface StockAdjustDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  product: ProductResponse | null;
}

export function StockAdjustDialog({ open, onOpenChange, product }: StockAdjustDialogProps) {
  const adjustStock = useAdjustStock();
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<StockFormInput, unknown, StockForm>({
    resolver: zodResolver(stockSchema),
    defaultValues: { quantityChange: 0, reason: "RESTOCK" },
  });

  useEffect(() => {
    if (open) reset({ quantityChange: 0, reason: "RESTOCK" });
  }, [open, reset]);

  if (!product) return null;

  const onSubmit = async (values: StockForm) => {
    await adjustStock.mutateAsync({
      id: product.id,
      body: { quantityChange: values.quantityChange, reason: values.reason },
    });
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Adjust stock &mdash; {product.name}</DialogTitle>
          <DialogDescription>
            Current stock: {product.stockQuantity}. Enter a positive number to restock, or
            negative to correct a miscount.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-2">
            <Label htmlFor="quantityChange">Quantity change</Label>
            <Input
              id="quantityChange"
              type="number"
              placeholder="e.g. 50 or -5"
              {...register("quantityChange")}
            />
            {errors.quantityChange && (
              <p className="text-sm text-destructive">{errors.quantityChange.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="reason">Reason</Label>
            <Select
              value={watch("reason")}
              onValueChange={(value) => setValue("reason", value as InventoryReason)}
            >
              <SelectTrigger id="reason" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="RESTOCK">Restock</SelectItem>
                <SelectItem value="ORDER_CANCELLED">Order cancelled (manual restock)</SelectItem>
                <SelectItem value="ORDER_PLACED">Correction (order placed)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={adjustStock.isPending}>
              {adjustStock.isPending ? "Saving..." : "Adjust stock"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
