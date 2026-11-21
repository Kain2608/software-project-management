import { httpClient } from '../../shared/api/httpClient.js'
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function config(token,signal){if(typeof token!=='string'||!token.trim())throw new Error('AUTHENTICATION_REQUIRED');return{signal,headers:{Authorization:`Bearer ${token}`}}}
export async function loadDeviceHistory({deviceId,accessToken,limit=50,offset=0,signal},client=httpClient){
  if(typeof deviceId!=='string'||!UUID.test(deviceId))throw new Error('INVALID_DEVICE')
  const {data}=await client.get(`/v1/devices/${deviceId}/history`,{...config(accessToken,signal),params:{limit,offset}})
  if(data?.device_id?.toLowerCase()!==deviceId.toLowerCase()||!Array.isArray(data?.events))throw new Error('HISTORY_CONTRACT_INVALID')
  return data
}
export function classifyHistoryError(error){const status=error?.response?.status;if(error?.message==='AUTHENTICATION_REQUIRED'||status===401)return'auth';if(status===403)return'forbidden';if(status===404)return'not-found';return'error'}
