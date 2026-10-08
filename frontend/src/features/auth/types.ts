export interface AdminLoginPayload {
  email: string;
  password: string;
}

/** The same two fields as an admin: an agent signs in with their email as well. */
export type AgentLoginPayload = AdminLoginPayload;

export interface AgentSignupPayload {
  name: string;
  email: string;
  phone: string;
  address: string;
  /** Optional — agents who skip it sign in with an OTP, as they always have. */
  password?: string;
}

export interface OtpRequestResult {
  sent: boolean;
  /** Only populated outside production, so the demo never needs a real SMS gateway. */
  devOtp?: string;
}

export interface SignupResult {
  message: string;
  devOtp?: string;
}
