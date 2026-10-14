import { request } from './request'
import type {
  ProfileCheckResponse,
  ProfileResponse,
  ProfileUpdateRequest,
  TravelerRequest,
  TravelerResponse
} from '@/types/api'

export function getProfile() {
  return request<ProfileResponse>({ url: '/profile', method: 'get' })
}

export function updateProfile(data: ProfileUpdateRequest) {
  return request<ProfileResponse>({ url: '/profile', method: 'put', data })
}

export function checkProfile() {
  return request<ProfileCheckResponse>({ url: '/profile/check', method: 'post' })
}

export function listTravelers() {
  return request<TravelerResponse[]>({ url: '/profile/travelers', method: 'get' })
}

export function addTraveler(data: TravelerRequest) {
  return request<TravelerResponse>({ url: '/profile/travelers', method: 'post', data })
}

export function updateTraveler(travelerId: string, data: TravelerRequest) {
  return request<TravelerResponse>({
    url: `/profile/travelers/${travelerId}`,
    method: 'put',
    data
  })
}

export function deleteTraveler(travelerId: string) {
  return request<void>({ url: `/profile/travelers/${travelerId}`, method: 'delete' })
}
