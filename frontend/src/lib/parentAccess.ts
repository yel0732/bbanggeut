let isParentAccessGranted = false

export function grantParentAccess() {
  isParentAccessGranted = true
}

export function revokeParentAccess() {
  isParentAccessGranted = false
}

export function hasParentAccess() {
  return isParentAccessGranted
}
