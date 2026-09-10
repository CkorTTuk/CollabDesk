import { useEffect, useRef, useState } from 'react'
import { removeAvatar, uploadAvatar } from '../api/accountApi.js'
import { useI18n } from '../i18n/I18nProvider.jsx'

function initials(name = '') {
  return name.trim().split(/\s+/).slice(0, 2)
    .map((part) => part[0]?.toUpperCase()).join('') || '?'
}

export default function AvatarSection({ account, onAccountChange }) {
  const { t } = useI18n()
  const inputRef = useRef(null)
  const [selectedFile, setSelectedFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [error, setError] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [isRemoving, setIsRemoving] = useState(false)

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  function clearSelection() {
    setSelectedFile(null)
    setPreviewUrl(null)
    setError('')
    if (inputRef.current) inputRef.current.value = ''
  }

  function selectFile(event) {
    const file = event.target.files?.[0]
    setError('')
    if (!file) return clearSelection()
    if (file.size > 5 * 1024 * 1024) {
      clearSelection()
      setError(t('account.avatar.tooLarge'))
      return
    }
    setSelectedFile(file)
    setPreviewUrl(URL.createObjectURL(file))
  }

  async function save() {
    if (!selectedFile) return
    setIsUploading(true)
    setError('')
    try {
      const updated = await uploadAvatar(selectedFile)
      onAccountChange({ ...updated, onboardingCompleted: true })
      clearSelection()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsUploading(false)
    }
  }

  async function remove() {
    setIsRemoving(true)
    setError('')
    try {
      const updated = await removeAvatar()
      onAccountChange({ ...updated, onboardingCompleted: true })
      clearSelection()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsRemoving(false)
    }
  }

  const visibleAvatar = previewUrl || account.avatarUrl
  const busy = isUploading || isRemoving
  return (
    <div className="avatar-editor">
      <div className="avatar-editor-preview" aria-label={t('account.avatar.preview')}>
        {visibleAvatar
          ? <img src={visibleAvatar} alt="" />
          : initials(account.displayName)}
      </div>
      <div className="avatar-editor-controls">
        <input ref={inputRef} type="file" accept="image/jpeg,image/png" onChange={selectFile} disabled={busy} />
        <small>{selectedFile?.name || t('account.avatar.formats')}</small>
        {error && <small className="field-error" role="alert">{error}</small>}
        <div className="account-form-actions">
          {selectedFile && <button className="primary-button" type="button" disabled={busy} onClick={save}>{isUploading ? t('account.avatar.uploading') : t('account.avatar.save')}</button>}
          {selectedFile && <button className="ghost-button" type="button" disabled={busy} onClick={clearSelection}>{t('common.cancel')}</button>}
          {!selectedFile && account.avatarUrl && <button className="danger-button" type="button" disabled={busy} onClick={remove}>{isRemoving ? t('account.avatar.removing') : t('account.avatar.remove')}</button>}
        </div>
      </div>
    </div>
  )
}
