import { httpClient } from '../../shared/api/httpClient.js'

export async function getPosStatus() {
  const response = await httpClient.get('/pos/status')
  return response.data
}
