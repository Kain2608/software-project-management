import { BrowserQRCodeReader } from '@zxing/browser'
import { useCallback, useEffect, useRef, useState } from 'react'

import { classifyLookupError, lookupQrDevice } from './qrApi.js'
import { abbreviateQrToken, validateQrPayload } from './qrPayload.js'
import './qr-scanner.css'

const CAMERA_CONSTRAINTS = {
  audio: false,
  video: { facingMode: { ideal: 'environment' } },
}

function cameraErrorMessage(error) {
  if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
    return 'Trình duyệt chưa được cấp quyền camera. Hãy cấp quyền rồi thử lại.'
  }
  if (error?.name === 'NotFoundError' || error?.name === 'OverconstrainedError') {
    return 'Không tìm thấy camera phù hợp trên thiết bị này.'
  }
  return 'Không thể khởi động camera. Bạn vẫn có thể kiểm tra payload thủ công.'
}

function shortIdentifier(value) {
  if (typeof value !== 'string' || value.length < 16) return value ?? '—'
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

export default function QrCameraScanner({ accessToken = null }) {
  const videoRef = useRef(null)
  const controlsRef = useRef(null)
  const sessionRef = useRef(0)
  const mountedRef = useRef(true)
  const lastDecodedPayloadRef = useRef(null)
  const lastLookupPayloadRef = useRef(null)
  const lookupInFlightRef = useRef(false)

  const [cameraState, setCameraState] = useState('idle')
  const [cameraMessage, setCameraMessage] = useState('Camera chỉ được mở khi bạn bấm Bắt đầu quét.')
  const [manualPayload, setManualPayload] = useState('')
  const [validationResult, setValidationResult] = useState(null)
  const [lookupState, setLookupState] = useState('idle')
  const [lookupMessage, setLookupMessage] = useState('')
  const [device, setDevice] = useState(null)

  const authenticated = typeof accessToken === 'string' && accessToken.length > 0

  const releaseCamera = useCallback(() => {
    controlsRef.current?.stop()
    controlsRef.current = null

    const stream = videoRef.current?.srcObject
    if (typeof MediaStream !== 'undefined' && stream instanceof MediaStream) {
      stream.getTracks().forEach((track) => track.stop())
      videoRef.current.srcObject = null
    }
  }, [])

  const stopCamera = useCallback(() => {
    sessionRef.current += 1
    releaseCamera()
    lastDecodedPayloadRef.current = null
    lastLookupPayloadRef.current = null
    setCameraState('idle')
    setCameraMessage('Đã tắt camera.')
  }, [releaseCamera])

  const lookupPayload = useCallback(async (payload) => {
    if (!authenticated) {
      setLookupState('auth-required')
      setLookupMessage('Cần đăng nhập bằng nhà cung cấp danh tính đã được cấu hình trước khi tra cứu.')
      setDevice(null)
      return
    }
    if (lookupInFlightRef.current || lastLookupPayloadRef.current === payload) return

    lookupInFlightRef.current = true
    lastLookupPayloadRef.current = payload
    setLookupState('loading')
    setLookupMessage('Đang tra cứu thiết bị trong phạm vi được cấp quyền…')
    setDevice(null)

    try {
      const result = await lookupQrDevice(payload, accessToken)
      if (!mountedRef.current) return
      setDevice(result)
      setLookupState('success')
      setLookupMessage('Đã nhận dữ liệu thiết bị từ backend.')
    } catch (error) {
      if (!mountedRef.current) return
      const classified = classifyLookupError(error)
      setLookupState(classified.state)
      setLookupMessage(classified.message)
      setDevice(null)
      lastLookupPayloadRef.current = null
    } finally {
      lookupInFlightRef.current = false
    }
  }, [accessToken, authenticated])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      sessionRef.current += 1
      releaseCamera()
    }
  }, [releaseCamera])

  const handleDecodedPayload = useCallback((rawPayload, controls) => {
    if (rawPayload === lastDecodedPayloadRef.current) return

    lastDecodedPayloadRef.current = rawPayload
    const result = validateQrPayload(rawPayload)
    setManualPayload(rawPayload)
    setValidationResult(result)

    if (!result.valid) {
      setCameraMessage('Đã đọc QR nhưng payload không đúng hợp đồng SASMS dự kiến.')
      return
    }

    sessionRef.current += 1
    controls.stop()
    controlsRef.current = null
    setCameraState('decoded')
    setCameraMessage('Đã đọc qr_token hợp lệ. Đang chuyển sang bước tra cứu có xác thực.')
    void lookupPayload(rawPayload)
  }, [lookupPayload])

  const startCamera = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia || !videoRef.current) {
      setCameraState('error')
      setCameraMessage('Trình duyệt này không hỗ trợ truy cập camera an toàn.')
      return
    }

    sessionRef.current += 1
    const currentSession = sessionRef.current
    releaseCamera()
    lastDecodedPayloadRef.current = null
    lastLookupPayloadRef.current = null
    setValidationResult(null)
    setLookupState('idle')
    setLookupMessage('')
    setDevice(null)
    setCameraState('requesting')
    setCameraMessage('Đang yêu cầu quyền sử dụng camera…')

    const reader = new BrowserQRCodeReader(undefined, {
      delayBetweenScanAttempts: 150,
      delayBetweenScanSuccess: 800,
    })

    try {
      const controls = await reader.decodeFromConstraints(
        CAMERA_CONSTRAINTS,
        videoRef.current,
        (result, _error, activeControls) => {
          // Frame-level decode errors are expected while no QR is visible.
          if (result) handleDecodedPayload(result.getText(), activeControls)
        },
      )

      if (!mountedRef.current || sessionRef.current !== currentSession) {
        controls.stop()
        return
      }

      controlsRef.current = controls
      setCameraState('scanning')
      setCameraMessage('Camera đang quét. Đưa mã QR vào giữa khung hình.')
    } catch (error) {
      if (!mountedRef.current || sessionRef.current !== currentSession) return

      releaseCamera()
      setCameraState('error')
      setCameraMessage(cameraErrorMessage(error))
    }
  }, [handleDecodedPayload, releaseCamera])

  const checkManualPayload = () => {
    const result = validateQrPayload(manualPayload)
    setValidationResult(result)
    if (result.valid) void lookupPayload(manualPayload)
  }

  const cameraActive = cameraState === 'requesting' || cameraState === 'scanning'

  return (
    <section className="qr-panel" aria-labelledby="qr-scanner-title">
      <div className="qr-heading">
        <div>
          <p className="qr-kicker">S1-08 · QR DEVICE LOOKUP</p>
          <h2 id="qr-scanner-title">Quét QR thiết bị</h2>
          <p className="qr-lead">
            Đọc qr_token từ camera hoặc nhập thủ công, sau đó tra cứu backend bằng phiên JWT đã xác thực.
          </p>
        </div>
        <span className="qr-contract-badge">Hợp đồng DRAFT</span>
      </div>

      {!authenticated && (
        <div className="qr-auth-notice" role="status">
          <strong>Chưa có phiên xác thực</strong>
          <span>Camera và validation cục bộ vẫn dùng được; API sẽ không được gọi cho tới khi lớp đăng nhập cung cấp access token.</span>
        </div>
      )}

      <div className="qr-layout">
        <div className="qr-camera-card">
          <div className={`qr-preview qr-preview--${cameraState}`}>
            <video ref={videoRef} muted playsInline aria-label="Hình ảnh camera quét QR" />
            <div className="qr-viewfinder" aria-hidden="true"><span /></div>
            {!cameraActive && cameraState !== 'decoded' && (
              <div className="qr-preview-placeholder">
                <span className="qr-camera-icon" aria-hidden="true">▣</span>
                <strong>Camera đang tắt</strong>
                <small>Quyền camera chỉ được yêu cầu khi bạn chủ động bắt đầu.</small>
              </div>
            )}
          </div>

          <div className="qr-camera-status" role="status" aria-live="polite">
            <span className={`qr-status-dot qr-status-dot--${cameraState}`} />
            <span>{cameraMessage}</span>
          </div>

          <div className="qr-actions">
            <button
              className="qr-button qr-button--primary"
              type="button"
              onClick={startCamera}
              disabled={cameraActive}
            >
              {cameraState === 'requesting' ? 'Đang mở camera…' : 'Bắt đầu quét'}
            </button>
            <button
              className="qr-button qr-button--secondary"
              type="button"
              onClick={stopCamera}
              disabled={!cameraActive}
            >
              Dừng camera
            </button>
          </div>
        </div>

        <div className="qr-manual-card">
          <div>
            <p className="qr-step-label">Kiểm tra thủ công</p>
            <h3>Nhập payload QR</h3>
            <p>Dùng khi thiết bị không có camera hoặc quyền camera bị từ chối.</p>
          </div>

          <label className="qr-field" htmlFor="manual-qr-payload">
            Payload
            <textarea
              id="manual-qr-payload"
              value={manualPayload}
              onChange={(event) => {
                setManualPayload(event.target.value)
                setValidationResult(null)
                lastLookupPayloadRef.current = null
                setLookupState('idle')
                setLookupMessage('')
                setDevice(null)
              }}
              placeholder="SASMS:DEVICE:123e4567-e89b-12d3-a456-426614174000"
              rows="4"
              spellCheck="false"
            />
          </label>

          <button
            className="qr-button qr-button--dark"
            type="button"
            onClick={checkManualPayload}
            disabled={lookupState === 'loading'}
          >
            {lookupState === 'loading' ? 'Đang tra cứu…' : 'Kiểm tra và tra cứu'}
          </button>

          {validationResult && (
            <div
              className={`qr-result qr-result--${validationResult.valid ? 'valid' : 'invalid'}`}
              role={validationResult.valid ? 'status' : 'alert'}
            >
              <strong>{validationResult.valid ? 'Payload hợp lệ' : 'Payload không hợp lệ'}</strong>
              <span>
                {validationResult.valid
                  ? `qr_token: ${abbreviateQrToken(validationResult.qrToken)}`
                  : validationResult.message}
              </span>
            </div>
          )}

          {lookupState !== 'idle' && (
            <div className={`qr-lookup qr-lookup--${lookupState}`} aria-live="polite">
              <strong>
                {lookupState === 'success' ? 'Kết quả thiết bị' :
                  lookupState === 'loading' ? 'Đang xử lý' :
                    lookupState === 'empty' ? 'Không có kết quả' :
                      lookupState === 'auth-required' ? 'Cần xác thực' :
                        lookupState === 'forbidden' ? 'Không đủ quyền' : 'Tra cứu thất bại'}
              </strong>
              <span>{lookupMessage}</span>
              {lookupState === 'success' && device && (
                <dl className="qr-device-grid">
                  <div><dt>Device</dt><dd>{shortIdentifier(device.device_id)}</dd></div>
                  <div><dt>Product</dt><dd>{shortIdentifier(device.product_id)}</dd></div>
                  <div><dt>Warehouse</dt><dd>{shortIdentifier(device.warehouse_id)}</dd></div>
                  <div><dt>Trạng thái</dt><dd>{device.status}</dd></div>
                </dl>
              )}
            </div>
          )}

          <div className="qr-contract-note">
            <strong>Định dạng đang áp dụng</strong>
            <code>SASMS:DEVICE:&lt;uuid-viết-thường&gt;</code>
            <span>Không tự trim khoảng trắng, không nhận URL và không suy diễn device_id.</span>
          </div>
        </div>
      </div>
    </section>
  )
}
