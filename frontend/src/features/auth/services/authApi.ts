import { request } from '@/lib/apiClient';
import type { AuthSession, AuthUser } from '@/shared/types';
import type { AdminLoginPayload, AgentSignupPayload, OtpRequestResult, SignupResult } from '../types';

export const authApi = {
  adminLogin: (payload: AdminLoginPayload) =>
    request<AuthSession>('/api/auth/admin/login', { method: 'POST', body: payload }),

  requestOtp: (phone: string) =>
    request<OtpRequestResult>('/api/auth/agent/otp/request', { method: 'POST', body: { phone } }),

  verifyOtp: (phone: string, otp: string) =>
    request<AuthSession>('/api/auth/agent/otp/verify', { method: 'POST', body: { phone, otp } }),

  agentSignup: (payload: AgentSignupPayload) =>
    request<SignupResult>('/api/auth/agent/signup', { method: 'POST', body: payload }),

  me: () => request<AuthUser>('/api/auth/me'),

  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
};
