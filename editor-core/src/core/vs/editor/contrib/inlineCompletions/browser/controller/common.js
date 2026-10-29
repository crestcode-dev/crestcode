/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
let _getInlineCompletionsController;
export function getInlineCompletionsController(editor) {
    return _getInlineCompletionsController?.(editor) ?? null;
}
export function setInlineCompletionsControllerGetter(getter) {
    _getInlineCompletionsController = getter;
}
//# sourceMappingURL=common.js.map