import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useCart } from "@/hooks/use-cart";
import { useCheckout } from "@/hooks/use-orders";
import { formatCurrency } from "@/utils/format";
import { getErrorMessage } from "@/api/client";

const checkoutSchema = z.object({
  paymentMethodToken: z.string().min(1, "Enter a payment method token"),
});

type CheckoutForm = z.infer<typeof checkoutSchema>;

export function CheckoutPage() {
  const { data: cart, isLoading } = useCart();
  const checkout = useCheckout();
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CheckoutForm>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: { paymentMethodToken: "tok_visa" },
  });

  if (isLoading) return <Skeleton className="h-96 w-full rounded-lg" />;
  if (!cart || cart.items.length === 0) return <Navigate to="/cart" replace />;

  const onSubmit = async (values: CheckoutForm) => {
    setIsSubmitting(true);
    try {
      const order = await checkout.mutateAsync(values);
      if (order.status === "PAYMENT_FAILED") {
        toast.error("Payment declined. Your cart has been preserved so you can retry.");
      } else {
        toast.success("Order placed successfully!");
      }
      navigate(`/orders/${order.id}`, { replace: true });
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">Checkout</h1>

      <Card>
        <CardHeader>
          <CardTitle>Order summary</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {cart.items.map((item) => (
            <div key={item.id} className="flex justify-between text-sm">
              <span>
                {item.product.name} &times; {item.quantity}
              </span>
              <span>{formatCurrency(item.lineTotal)}</span>
            </div>
          ))}
          <div className="flex justify-between border-t pt-2 font-medium">
            <span>Total</span>
            <span>{formatCurrency(cart.totalAmount)}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Payment</CardTitle>
          <CardDescription>
            This project uses a mock payment gateway &mdash; no real charge occurs. Use{" "}
            <code className="rounded bg-muted px-1 py-0.5">tok_visa</code> (or anything else) to
            simulate success, or{" "}
            <code className="rounded bg-muted px-1 py-0.5">tok_chargeDeclined</code> to simulate a
            decline.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="paymentMethodToken">Payment method token</Label>
              <Input id="paymentMethodToken" {...register("paymentMethodToken")} />
              {errors.paymentMethodToken && (
                <p className="text-sm text-destructive">{errors.paymentMethodToken.message}</p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? "Processing..." : `Pay ${formatCurrency(cart.totalAmount)}`}
            </Button>
          </form>
        </CardContent>
      </Card>

      <p className="text-center text-sm text-muted-foreground">
        <Link to="/cart" className="underline underline-offset-4">
          Back to cart
        </Link>
      </p>
    </div>
  );
}
