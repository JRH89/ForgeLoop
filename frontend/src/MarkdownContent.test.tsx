import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import MarkdownContent from "./MarkdownContent";

it("renders GitHub-style headings and lists as Markdown", () => {
  render(
    <MarkdownContent
      content={"## Problem\n\nA short description.\n\n## Acceptance criteria\n\n- First requirement\n- Second requirement"}
    />,
  );

  expect(screen.getByRole("heading", { name: "Problem", level: 2 })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Acceptance criteria", level: 2 })).toBeInTheDocument();
  expect(screen.getAllByRole("listitem")).toHaveLength(2);
  expect(screen.getByText("First requirement")).toBeInTheDocument();
});

it("does not interpret embedded raw HTML", () => {
  render(<MarkdownContent content={'<img src=x onerror="alert(1)">'} />);
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(screen.getByText('<img src=x onerror="alert(1)">')).toBeInTheDocument();
});