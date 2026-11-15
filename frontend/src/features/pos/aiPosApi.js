import { httpClient } from '../../shared/api/httpClient.js'
import { fetchVerifiedCrossSell } from './aiPosService.js'

export function getVerifiedCrossSell(context) {
  return fetchVerifiedCrossSell(context, httpClient)
}
