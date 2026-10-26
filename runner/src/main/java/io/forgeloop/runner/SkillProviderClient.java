package io.forgeloop.runner;

/** Supplies one pinned procedure selection before the existing provider and recording boundaries. */
final class SkillProviderClient implements ProviderClient {
    private final ProviderClient delegate;
    private final SkillSelection selection;
    SkillProviderClient(ProviderClient delegate, SkillSelection selection) {
        if (delegate == null || selection == null) throw new IllegalArgumentException("Skill provider inputs are required");
        this.delegate = delegate; this.selection = selection;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        return delegate.execute(request.withSkills(selection));
    }
}
