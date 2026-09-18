package io.forgeloop.harness;
public record ModelSelection(String provider, String model, String rationale) { public ModelSelection { if(provider.isBlank()||model.isBlank()||rationale.isBlank()) throw new IllegalArgumentException("Model selection must be explainable"); } }
