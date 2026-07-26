import { Link, NavLink, Outlet } from "react-router-dom";
import { LayoutGrid, LogOut, Package, ShoppingCart, User as UserIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/use-cart";
import { cn } from "@/lib/utils";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "text-sm font-medium transition-colors hover:text-foreground",
    isActive ? "text-foreground" : "text-muted-foreground",
  );

export function RootLayout() {
  const { user, logout } = useAuth();
  const { data: cart } = useCart();
  const itemCount = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
          <Link to="/" className="flex items-center gap-2 font-semibold">
            <LayoutGrid className="size-5" />
            <span>Ecommerce</span>
          </Link>

          <nav className="hidden items-center gap-6 sm:flex">
            <NavLink to="/products" className={navLinkClass}>
              Products
            </NavLink>
            {user && (
              <NavLink to="/orders" className={navLinkClass}>
                My Orders
              </NavLink>
            )}
            {user?.role === "ADMIN" && (
              <>
                <NavLink to="/admin/products" className={navLinkClass}>
                  Manage Products
                </NavLink>
                <NavLink to="/admin/orders" className={navLinkClass}>
                  All Orders
                </NavLink>
              </>
            )}
          </nav>

          <div className="flex items-center gap-2">
            {user && (
              <Button variant="ghost" size="icon" asChild className="relative">
                <Link to="/cart" aria-label="View cart">
                  <ShoppingCart className="size-5" />
                  {itemCount > 0 && (
                    <Badge className="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full p-0 text-[10px]">
                      {itemCount}
                    </Badge>
                  )}
                </Link>
              </Button>
            )}

            {user ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm" className="gap-2">
                    <UserIcon className="size-4" />
                    <span className="hidden max-w-32 truncate sm:inline">{user.fullName}</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <div className="px-2 py-1.5 text-xs text-muted-foreground">
                    {user.email} &middot; {user.role}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/orders">
                      <Package className="size-4" />
                      My Orders
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => logout()} variant="destructive">
                    <LogOut className="size-4" />
                    Log out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" asChild>
                  <Link to="/login">Log in</Link>
                </Button>
                <Button size="sm" asChild>
                  <Link to="/register">Sign up</Link>
                </Button>
              </div>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t py-6 text-center text-sm text-muted-foreground">
        Ecommerce Backend Portfolio Project
      </footer>
    </div>
  );
}
