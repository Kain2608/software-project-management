import { httpClient } from '../../shared/api/httpClient.js'
import { fetchVerifiedCrossSell, sendAiTelemetryEvent } from './aiPosService.js'

export function getVerifiedCrossSell(context) {
  return fetchVerifiedCrossSell(context, httpClient)
}

export function recordAiEvent(eventData) {
  return sendAiTelemetryEvent(eventData, httpClient)
}
