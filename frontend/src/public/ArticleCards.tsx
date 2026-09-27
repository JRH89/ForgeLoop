import { articles } from './articles';

/** One real link covers each card, including its visible read action. */
export function ArticleCards({ limit }: { limit?: number }) {
  return <div className="article-grid">{articles.slice(0, limit).map(article =>
    <a className="article-card" href={`/blog/${article.slug}`} key={article.slug}>
      <p className="eyebrow">{article.category}</p>
      <h3>{article.title}</h3><p>{article.description}</p>
      <span>Read field note <span aria-hidden="true">↗</span></span>
    </a>
  )}</div>;
}
