import { describe, it, expect } from 'vitest'
import { yuanToCents } from '@/api/expert'

describe('expert API helpers', () => {
  it('发布价格以元输入、按分提交', () => {
    expect(yuanToCents(0)).toBe(0)
    expect(yuanToCents(19.9)).toBe(1990)
    expect(yuanToCents(199.99)).toBe(19999)
  })
})
