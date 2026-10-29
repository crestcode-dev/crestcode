/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
import { observableValue } from '../../../base/common/observable.js';
import { createDecorator } from '../../../platform/instantiation/common/instantiation.js';
export const IRenameSymbolTrackerService = createDecorator('renameSymbolTrackerService');
export class NullRenameSymbolTrackerService {
    constructor() {
        this._trackedWord = observableValue(this, undefined);
        this.trackedWord = this._trackedWord;
        this._trackedWord.set(undefined, undefined);
    }
}
//# sourceMappingURL=renameSymbolTrackerService.js.map