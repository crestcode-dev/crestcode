import { autorun } from '../reactions/autorun.js';
import { transaction } from '../transaction.js';
import { observableValue } from '../observables/observableValue.js';
/**
 * A promise whose state is observable.
 */
export class ObservablePromise {
    static fromFn(fn) {
        return new ObservablePromise(fn());
    }
    constructor(promise) {
        this._value = observableValue(this, undefined);
        /**
         * The current state of the promise.
         * Is `undefined` if the promise didn't resolve yet.
         */
        this.promiseResult = this._value;
        this.promise = promise.then(value => {
            transaction(tx => {
                /** @description onPromiseResolved */
                this._value.set(new PromiseResult(value, undefined), tx);
            });
            return value;
        }, error => {
            transaction(tx => {
                /** @description onPromiseRejected */
                this._value.set(new PromiseResult(undefined, error), tx);
            });
            throw error;
        });
    }
}
export class PromiseResult {
    constructor(
    /**
     * The value of the resolved promise.
     * Undefined if the promise rejected.
     */
    data, 
    /**
     * The error in case of a rejected promise.
     * Undefined if the promise resolved.
     */
    error) {
        this.data = data;
        this.error = error;
    }
    /**
     * Returns the value if the promise resolved, otherwise throws the error.
     */
    getDataOrThrow() {
        if (this.error) {
            throw this.error;
        }
        return this.data;
    }
}
/**
 * Tracks a changing {@link ObservablePromise}, exposing the last resolved value
 * and whether a newer promise is still pending.
 */
export class ObservableResolvedPromise {
    constructor(source, initialValue, store) {
        this._isResolving = observableValue(this, false);
        this.isResolving = this._isResolving;
        this._lastResolved = observableValue(this, initialValue);
        this.lastResolved = this._lastResolved;
        store.add(autorun(reader => {
            const current = source.read(reader);
            this._runningPromise = current;
            const result = current.promiseResult.read(reader);
            if (result) {
                if (current === this._runningPromise) {
                    this._isResolving.set(false, undefined);
                    this._lastResolved.set(result.getDataOrThrow(), undefined);
                }
            }
            else {
                this._isResolving.set(true, undefined);
            }
        }));
    }
}
//# sourceMappingURL=promise.js.map