import { afterEach } from 'vitest'
import { resetCsrf } from '../api'

afterEach(() => {
  resetCsrf()
  sessionStorage.clear()
})
