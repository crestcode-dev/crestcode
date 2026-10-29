/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
/**
 * A resource participating on one side of a document diff.
 */
export class DiffItemSource {
    constructor(uri, textModel) {
        this.uri = uri;
        this.textModel = textModel;
    }
}
//# sourceMappingURL=model.js.map