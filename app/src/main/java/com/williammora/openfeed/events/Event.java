package com.williammora.openfeed.events;

public abstract class Event<T> {

    protected T result;

    public Event(T result) {
        this.result = result;
    }

    public T getResult() {
        return result;
    }
}
