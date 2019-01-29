package org.mib.cochat.context;

import org.mib.cochat.chatter.Chatter;

public class CochatScope {

    private static final ThreadLocal<CochatContext> CONTEXT_HOLDER = ThreadLocal.withInitial(() -> null);

    public static CochatContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    public static Chatter getChatter() {
        CochatContext context = getContext();
        if (context == null) {
            throw new IllegalStateException("no chatter set in context");
        }
        return context.getChatter();
    }

    public static void setChatter(Chatter chatter) {
        CONTEXT_HOLDER.set(new CochatContext(chatter));
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
