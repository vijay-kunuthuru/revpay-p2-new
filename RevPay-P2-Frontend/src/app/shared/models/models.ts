export interface LoginRequest {
  email?: string;
  password?: string;
}

export interface SignupRequest {
  fullName?: string;
  email?: string;
  phoneNumber?: string;
  password?: string;
  transactionPin?: string;
  role?: string;
  securityQuestion?: string;
  securityAnswer?: string;
}

export interface JwtResponse {
  token: string;
  userId: number;
  email: string;
  role?: string;
  roles?: string[];
}

export interface ApiResponse<T> {
  data: T;
  message: string;
  success: boolean;
}

export interface Transaction {
  transactionId: number;
  senderId: number;
  receiverId: number;
  amount: number;
  type: string;
  status: string;
  description: string;
  timestamp: string;
  transactionRef: string;
}

export interface PaymentMethodDTO {
  id: number;
  partialCardNumber: string;
  expiryDate: string;
  billingAddress: string;
  isDefault: boolean;
  cardType: string;
}

export interface CardPayload {
  cardNumber: string;
  expiryDate: string;
  cvv: string;
  billingAddress?: string;
  cardType?: string;
}

export interface UpdatePasswordRequest {
  oldPassword?: string;
  newPassword?: string;
}
