// Mirrors the backend DTOs 1:1 (see backend/src/main/java/com/ecommerce/dto and entity enums).

export type Role = "CUSTOMER" | "ADMIN";

export type OrderStatus =
  | "PENDING"
  | "PAID"
  | "PAYMENT_FAILED"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";

export type PaymentStatus = "SUCCEEDED" | "FAILED";

export type InventoryReason = "ORDER_PLACED" | "ORDER_CANCELLED" | "RESTOCK";

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

// ---- Auth ----------------------------------------------------------------

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string;
}

// ---- Products --------------------------------------------------------------

export interface ProductResponse {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  price: string;
  stockQuantity: number;
  category: string | null;
  active: boolean;
}

export interface ProductCreateRequest {
  sku: string;
  name: string;
  description?: string;
  price: number;
  stockQuantity: number;
  category?: string;
}

export interface ProductUpdateRequest {
  sku: string;
  name: string;
  description?: string;
  price: number;
  category?: string;
}

export interface StockAdjustRequest {
  quantityChange: number;
  reason: InventoryReason;
}

// ---- Cart ------------------------------------------------------------------

export interface CartItemResponse {
  id: number;
  product: ProductResponse;
  quantity: number;
  lineTotal: string;
}

export interface CartResponse {
  id: number;
  items: CartItemResponse[];
  totalAmount: string;
}

export interface CartItemAddRequest {
  productId: number;
  quantity: number;
}

export interface CartItemUpdateRequest {
  quantity: number;
}

// ---- Orders ------------------------------------------------------------------

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  unitPrice: string;
  quantity: number;
  lineTotal: string;
}

export interface PaymentResponse {
  id: number;
  gateway: string;
  gatewayReferenceId: string;
  amount: string;
  status: string;
  createdAt: string;
}

export interface OrderResponse {
  id: number;
  status: OrderStatus;
  totalAmount: string;
  createdAt: string;
  items: OrderItemResponse[];
  payment: PaymentResponse | null;
}

export interface CheckoutRequest {
  paymentMethodToken: string;
}

export interface OrderStatusUpdateRequest {
  status: OrderStatus;
}
