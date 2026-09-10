export const EMPTY_BIRTH_DATE = {
  month: '',
  day: '',
  year: '',
}

export function parseBirthDate(value) {
  if (!value) return EMPTY_BIRTH_DATE
  const [year, month, day] = value.split('-')
  return { year, month, day }
}

export function formatBirthDate({ year, month, day }) {
  return year && month && day ? `${year}-${month}-${day}` : null
}

export function hasPartialBirthDate({ year, month, day }) {
  const selectedParts = [year, month, day].filter(Boolean).length
  return selectedParts > 0 && selectedParts < 3
}
