'use strict';
function typeOf(value) {
  if (value === undefined) return 'missing';
  if (value === null) return 'null';
  return Array.isArray(value) ? 'array' : typeof value;
}
function responseShape(response) {
  const body = response?.data;
  return {
    httpStatus: Number.isInteger(response?.status) ? response.status : null,
    bodyType: typeOf(body),
    applicationStatusType: typeOf(body?.statusCode),
    applicationStatus: Number.isInteger(body?.statusCode) ? body.statusCode : null,
    dataType: typeOf(body?.data),
    nestedEligibilityType: typeOf(body?.data?.isClaimable),
    topLevelEligibilityType: typeOf(body?.isClaimable)
  };
}
function parseEligibility(response) {
  const body = response?.data;
  const httpOk = Number.isInteger(response?.status) && response.status >= 200 && response.status < 300;
  // The original eligibility client read data.isClaimable without requiring statusCode.
  // Missing application status is allowed, but explicit failures are never ignored.
  const applicationOk = body?.statusCode === undefined || body.statusCode === 200;
  if (!httpOk || !applicationOk || typeof body?.data?.isClaimable !== 'boolean') {
    const error = new Error('invalid_eligibility_response');
    error.response = response;
    throw error;
  }
  return body.data.isClaimable;
}
module.exports = { parseEligibility, responseShape };
