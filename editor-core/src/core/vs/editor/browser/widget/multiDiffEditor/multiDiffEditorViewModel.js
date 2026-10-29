/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
import { Disposable, DisposableStore, toDisposable } from '../../../../base/common/lifecycle.js';
import { ObservablePromise, ObservableResolvedPromise, constObservable, derived, derivedObservableWithWritableCache, mapObservableArrayCached, observableFromValueWithChangeEvent, observableValue } from '../../../../base/common/observable.js';
import { rejectIfNotCanceled, timeout } from '../../../../base/common/async.js';
import { IInstantiationService } from '../../../../platform/instantiation/common/instantiation.js';
import { ITextModelService } from '../../../common/services/resolverService.js';
import { isDefined } from '../../../../base/common/types.js';
import { DiffEditorOptions } from '../diffEditor/diffEditorOptions.js';
import { DiffEditorViewModel } from '../diffEditor/diffEditorViewModel.js';
import { RefCounted } from '../diffEditor/utils.js';
import { cancelOnDispose } from '../../../../base/common/cancellation.js';
let MultiDiffEditorViewModel = class MultiDiffEditorViewModel extends Disposable {
    get contextKeys() {
        return this.model.contextKeys;
    }
    constructor(model, _instantiationService, _textModelService) {
        super();
        this.model = model;
        this._instantiationService = _instantiationService;
        this._textModelService = _textModelService;
        this._documentsArr = derived(this, reader => {
            const result = this._documents.read(reader);
            if (result === 'loading') {
                return [];
            }
            return result;
        });
        this.focusedDiffItem = derived(this, reader => this.items.read(reader).find(i => i.isFocused.read(reader)));
        this.activeDiffItem = derivedObservableWithWritableCache(this, (reader, lastValue) => this.focusedDiffItem.read(reader) ?? (lastValue && this.items.read(reader).indexOf(lastValue) !== -1 ? lastValue : undefined));
        this._documents = observableFromValueWithChangeEvent(this.model, this.model.documents);
        const allItems = mapObservableArrayCached(this, this._documentsArr, (d, store) => this._createItem(d, store)).recomputeInitiallyAndOnChange(this._store);
        this._waitForNewDiffs = derived(this, reader => {
            const pending = allItems.read(reader);
            return ObservablePromise.fromFn(async () => {
                const next = (await Promise.all(pending)).filter(isDefined);
                await Promise.all(next.map(i => i.object.waitForInitialDiffOr1s.promise));
                return next;
            });
        });
        const resolved = new ObservableResolvedPromise(this._waitForNewDiffs, [], this._store);
        this.items = derived(this, reader => {
            const resolvedItems = resolved.lastResolved.read(reader);
            return resolvedItems.map(i => {
                const ref = reader.store.add(i.createNewRef(i));
                return ref.object;
            });
        });
        this.isLoading = derived(this, reader => this._documents.read(reader) === 'loading' || resolved.isResolving.read(reader));
    }
    async _createItem(document, store) {
        const resources = new DisposableStore();
        let transferred = false;
        try {
            const documentReference = resources.add(document.createNewRef(this));
            const original = document.object.original?.textModel ?? resources.add(await this._textModelService.createSyntheticDocument('', null)).object.textEditorModel;
            if (store.isDisposed) {
                return undefined;
            }
            const modified = document.object.modified?.textModel ?? resources.add(await this._textModelService.createSyntheticDocument('', null)).object.textEditorModel;
            if (store.isDisposed) {
                return undefined;
            }
            const item = this._instantiationService.createInstance(DocumentDiffItemViewModel, documentReference, this, original, modified, resources);
            const reference = store.add(RefCounted.create(item));
            transferred = true;
            return reference;
        }
        finally {
            if (!transferred) {
                resources.dispose();
            }
        }
    }
};
MultiDiffEditorViewModel = __decorate([
    __param(1, IInstantiationService),
    __param(2, ITextModelService)
], MultiDiffEditorViewModel);
export { MultiDiffEditorViewModel };
let DocumentDiffItemViewModel = class DocumentDiffItemViewModel extends Disposable {
    get diffEditorViewModel() {
        return this.diffEditorViewModelRef.object;
    }
    get originalUri() { return this.documentDiffItem.original?.uri; }
    get modifiedUri() { return this.documentDiffItem.modified?.uri; }
    get isBinary() {
        const { original, modified } = this.documentDiffItem;
        return (original !== undefined && original.textModel === undefined)
            || (modified !== undefined && modified.textModel === undefined);
    }
    setActive(tx) {
        this._editorViewModel.activeDiffItem.setCache(this, tx);
    }
    setIsFocused(source, tx) {
        this._isFocusedSource.set(source, tx);
    }
    get documentDiffItem() {
        return this._documentDiffItemRef.object;
    }
    constructor(_documentDiffItemRef, _editorViewModel, originalTextModel, modifiedTextModel, diffEditorViewModelStore, _instantiationService) {
        super();
        this._documentDiffItemRef = _documentDiffItemRef;
        this._editorViewModel = _editorViewModel;
        this._instantiationService = _instantiationService;
        this.collapsed = observableValue(this, false);
        this.lastTemplateData = observableValue(this, { expandedContentHeight: 500, selections: undefined, });
        this.isActive = derived(this, reader => this._editorViewModel.activeDiffItem.read(reader) === this);
        this.isFirst = derived(this, reader => this._editorViewModel.items.read(reader)[0] === this);
        this._isFocusedSource = observableValue(this, constObservable(false));
        this.isFocused = derived(this, reader => this._isFocusedSource.read(reader).read(reader));
        this.isAlive = observableValue(this, true);
        this._register(toDisposable(() => {
            this.isAlive.set(false, undefined);
        }));
        function updateOptions(options) {
            return {
                ...options,
                hideUnchangedRegions: {
                    enabled: true,
                },
            };
        }
        const options = this._instantiationService.createInstance(DiffEditorOptions, updateOptions(this.documentDiffItem.options || {}));
        if (this.documentDiffItem.onOptionsDidChange) {
            this._register(this.documentDiffItem.onOptionsDidChange(() => {
                options.updateOptions(updateOptions(this.documentDiffItem.options || {}));
            }));
        }
        this.diffEditorViewModelRef = this._register(RefCounted.createWithDisposable(this._instantiationService.createInstance(DiffEditorViewModel, {
            original: originalTextModel,
            modified: modifiedTextModel,
        }, options), diffEditorViewModelStore, this));
        this.waitForInitialDiffOr1s = new ObservablePromise(Promise.race([
            this.diffEditorViewModel.waitForDiff().catch(rejectIfNotCanceled),
            timeout(1000, cancelOnDispose(this._store)).catch(rejectIfNotCanceled),
        ]));
    }
    getKey() {
        return JSON.stringify([
            this.originalUri?.toString(),
            this.modifiedUri?.toString()
        ]);
    }
};
DocumentDiffItemViewModel = __decorate([
    __param(5, IInstantiationService)
], DocumentDiffItemViewModel);
export { DocumentDiffItemViewModel };
//# sourceMappingURL=multiDiffEditorViewModel.js.map