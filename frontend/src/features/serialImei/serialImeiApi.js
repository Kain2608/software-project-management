import { httpClient } from '../../shared/api/httpClient.js'

export async function getSerialImeiStatus() {
  const response = await httpClient.get('/serial-imei/status')
  return response.data
}
