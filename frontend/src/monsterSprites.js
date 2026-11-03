import emptyCatch from './assets/lab/EMPTY_CATCH.svg'
import longMethod from './assets/lab/LONG_METHOD.svg'
import deepNesting from './assets/lab/DEEP_NESTING.svg'
import parameters from './assets/lab/TOO_MANY_PARAMETERS.svg'
import emptyIf from './assets/lab/EMPTY_IF.svg'

// Explicit imports let Vite resolve the same production URLs for both views.
export const monsterSprites = Object.freeze({
  EMPTY_CATCH: emptyCatch,
  LONG_METHOD: longMethod,
  DEEP_NESTING: deepNesting,
  TOO_MANY_PARAMETERS: parameters,
  EMPTY_IF: emptyIf,
})
