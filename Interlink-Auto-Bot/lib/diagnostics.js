'use strict';
// Only allowlisted metadata: never response bodies, URLs, headers or error messages.
function failureDetails(error, stage) {
  return {
    stage,
    httpStatus: Number.isInteger(error?.response?.status) ? error.response.status : null,
    code: ['ECONNABORTED', 'ETIMEDOUT', 'ENOTFOUND', 'ECONNRESET', 'ERR_CANCELED', 'ERR_NETWORK'].includes(error?.code) ? error.code : 'REQUEST_OR_RESPONSE_FAILED'
  };
}
module.exports = { failureDetails };
