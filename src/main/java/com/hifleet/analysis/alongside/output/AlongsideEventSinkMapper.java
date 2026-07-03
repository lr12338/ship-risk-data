package com.hifleet.analysis.alongside.output;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;

import java.io.Serializable;

public interface AlongsideEventSinkMapper extends Serializable {
    String toJson(AlongsideEvent event);
}
