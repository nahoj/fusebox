package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.nio.api.FuseboxFS;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public abstract class BaseFS implements ChainingFS {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    public abstract FuseboxFS delegate();

    @Override
    public void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
        delegate().init(conn, cfg);
        LOG.info("Initializing {}.", this.getClass().getSimpleName());
    }
}
