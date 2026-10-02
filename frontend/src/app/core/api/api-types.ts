import type { components } from './schema';

export type Schemas = components['schemas'];

export type ErrorCode = Schemas['ErrorCode'];
export type Money = Schemas['Money'];
export type Problem = Schemas['Problem'];
export type LoginRequest = Schemas['LoginRequest'];
export type SetPasswordRequest = Schemas['SetPasswordRequest'];
export type LoginResponse = Schemas['LoginResponse'];
export type Role = LoginResponse['role'];
