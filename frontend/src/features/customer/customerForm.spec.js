import { describe, it, expect } from 'vitest'
import {
  emptyForm,
  formatDomesticPhone,
  sizeChoices,
  visibleSizeFields,
  validateRegistration,
  toRegistrationBody,
} from './customerForm'

const table = [
  { itemType: 'OUTER', target: 'WOMEN', sizes: ['85', '90'] },
  { itemType: 'OUTER', target: 'MEN', sizes: ['95', '100'] },
  { itemType: 'TOP', target: null, sizes: ['S', 'M'] },
  { itemType: 'BOTTOM', target: 'WOMEN', sizes: ['24', '26'] },
  { itemType: 'BOTTOM', target: 'MEN', sizes: ['28', '30'] },
  { itemType: 'SHOES', target: null, sizes: ['240'] },
]

const filled = (patch = {}) => ({
  ...emptyForm(),
  name: '김하늘',
  phone: '010-2943-4821',
  privacy: true,
  age14: true,
  ...patch,
})

describe('formatDomesticPhone', () => {
  it('should_hyphenate_when_11_digits', () => {
    expect(formatDomesticPhone('01029434821')).toBe('010-2943-4821')
  })
  it('should_drop_non_digits_and_cut_at_11', () => {
    expect(formatDomesticPhone('010 2943-48219')).toBe('010-2943-4821')
  })
  it('should_keep_partial_input', () => {
    expect(formatDomesticPhone('0102')).toBe('010-2')
    expect(formatDomesticPhone('010')).toBe('010')
  })
})

describe('사이즈 칸 (REG-01: 성별이 없으면 아우터·하의 숨김)', () => {
  it('should_show_only_top_and_shoes_when_no_gender', () => {
    expect(visibleSizeFields(null)).toEqual(['top', 'shoes'])
  })
  it('should_show_all_four_when_gender_selected', () => {
    expect(visibleSizeFields('F')).toEqual(['top', 'outer', 'bottom', 'shoes'])
  })
  it('should_use_women_list_when_gender_F', () => {
    expect(sizeChoices(table, 'OUTER', 'F')).toEqual(['85', '90'])
    expect(sizeChoices(table, 'BOTTOM', 'M')).toEqual(['28', '30'])
  })
  it('should_use_common_list_when_item_has_no_target', () => {
    expect(sizeChoices(table, 'TOP', null)).toEqual(['S', 'M'])
  })
  it('should_return_empty_when_gendered_item_without_gender', () => {
    expect(sizeChoices(table, 'OUTER', null)).toEqual([])
  })
})

describe('validateRegistration', () => {
  it('should_pass_when_only_required_fields', () => {
    expect(validateRegistration(filled())).toEqual({})
  })
  it('should_require_name_phone_and_two_consents', () => {
    const errors = validateRegistration(emptyForm())
    expect(Object.keys(errors).sort()).toEqual(['age14', 'name', 'phone', 'privacy'])
  })
  it('should_reject_domestic_phone_not_starting_01', () => {
    expect(validateRegistration(filled({ phone: '02-123-4567' })).phone).toBeTruthy()
  })
  it('should_require_english_name_when_foreign', () => {
    const errors = validateRegistration(
      filled({ nationality: 'FOREIGN', name: '', phone: '+81 90 1234 5678' }),
    )
    expect(errors).toEqual({ nameEn: '영문 이름을 입력해 주세요' })
  })
  it('should_require_country_code_when_foreign', () => {
    const errors = validateRegistration(
      filled({ nationality: 'FOREIGN', nameEn: 'Aiko Sato', phone: '090-1234-5678' }),
    )
    expect(errors.phone).toBeTruthy()
  })
  it('should_reject_birth_year_out_of_range', () => {
    expect(validateRegistration(filled({ birthYear: '1899' })).birthYear).toBeTruthy()
    expect(validateRegistration(filled({ birthYear: '1991' })).birthYear).toBeUndefined()
  })
  it('should_require_both_month_and_day_when_birthday', () => {
    expect(validateRegistration(filled({ birthMonth: 3 })).birthMd).toBeTruthy()
  })
})

describe('toRegistrationBody (03 4.5, 직원 등록은 staffId)', () => {
  it('should_send_only_required_when_optional_empty', () => {
    expect(toRegistrationBody(filled({ staffId: 17 }))).toEqual({
      staffId: 17,
      nationality: 'DOMESTIC',
      name: '김하늘',
      phone: '010-2943-4821',
      agreements: { privacy: true, age14: true, marketingSms: false, marketingKakao: false },
    })
  })
  it('should_build_optional_fields', () => {
    const body = toRegistrationBody(
      filled({
        birthYear: '1991',
        birthMonth: 3,
        birthDay: 14,
        sido: '서울',
        gu: '강남구',
        detail: '[상세주소]',
        gender: 'F',
        preferredItemTypes: ['OUTER'],
        sizes: { top: 'M', outer: '90', bottom: '', shoes: '240' },
        contactChannel: 'KAKAO',
      }),
    )
    expect(body).toMatchObject({
      birthYear: 1991,
      birthMd: '0314',
      address: { sido: '서울', gu: '강남구', detail: '[상세주소]' },
      gender: 'F',
      preferredItemTypes: ['OUTER'],
      sizes: { top: 'M', outer: '90', shoes: '240' },
      contactChannel: 'KAKAO',
    })
  })
  it('should_drop_outer_and_bottom_when_gender_cleared', () => {
    const body = toRegistrationBody(
      filled({ sizes: { top: 'M', outer: '90', bottom: '26', shoes: '' } }),
    )
    expect(body.sizes).toEqual({ top: 'M' })
  })
  it('should_send_name_en_and_skip_empty_name_when_foreign', () => {
    const body = toRegistrationBody(
      filled({ nationality: 'FOREIGN', name: ' ', nameEn: 'Aiko Sato', phone: '+81 90-1234-5678' }),
    )
    expect(body.nameEn).toBe('Aiko Sato')
    expect(body.name).toBeUndefined()
    expect(body.phone).toBe('+819012345678')
  })
})
