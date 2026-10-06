import type { components } from './schema';

export type Schemas = components['schemas'];

export type ErrorCode = Schemas['ErrorCode'];
export type Money = Schemas['Money'];
export type Problem = Schemas['Problem'];
export type LoginRequest = Schemas['LoginRequest'];
export type SetPasswordRequest = Schemas['SetPasswordRequest'];
export type LoginResponse = Schemas['LoginResponse'];
export type Role = LoginResponse['role'];
export type Allergen = Schemas['MenuProduct']['allergens'][number];
export type Station = Schemas['StationResponse'];
export type SaveStationRequest = Schemas['SaveStationRequest'];
export type Category = Schemas['CategoryResponse'];
export type CreateCategoryRequest = Schemas['CreateCategoryRequest'];
export type UpdateCategoryRequest = Schemas['UpdateCategoryRequest'];
export type ModifierGroup = Schemas['ModifierGroupResponse'];
export type ModifierOption = Schemas['ModifierOptionResponse'];
export type SaveModifierGroupRequest = Schemas['SaveModifierGroupRequest'];
export type SaveModifierOptionRequest = Schemas['SaveModifierOptionRequest'];
export type Product = Schemas['ProductResponse'];
export type ProductPage = Schemas['PageResponseProductResponse'];
export type SaveProductRequest = Schemas['SaveProductRequest'];
export type Availability = Schemas['AvailabilityResponse'];
