package net.frankheijden.serverutils.common.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Template implements TagResolver.Single {

    private final TagResolver.Single resolver;

    private Template(TagResolver.Single resolver) {
        this.resolver = resolver;
    }

    public static Template of(String key, String value) {
        return new Template(Placeholder.unparsed(key, value));
    }

    public static Template of(String key, Component value) {
        return new Template(Placeholder.component(key, value));
    }

    @Override
    public String key() {
        return resolver.key();
    }

    @Override
    public Tag tag() {
        return resolver.tag();
    }
}
