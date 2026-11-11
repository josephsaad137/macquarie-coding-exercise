import type { Dataset } from '../api'
import { formatDate } from './formatters'

interface PageHeadingProps {
  eyebrow: string
  title: string
  subtitle: string
  dataset?: Dataset
}

export function PageHeading({ eyebrow, title, subtitle, dataset }: PageHeadingProps) {
  return (
    <section className="page-heading">
      <div>
        <div className="eyebrow"><span className="live-dot" /> {eyebrow}</div>
        <h1>{title}</h1>
        <p className="heading-subtitle">{subtitle}</p>
      </div>
      {dataset && (
        <div className="dataset-stamp">
          <span className="stamp-icon">▤</span>
          <span><strong>{dataset.filename}</strong><small>{title === 'Market overview' ? `Added ${formatDate(dataset.uploadedAt)}` : `${dataset.frequency} dataset`}</small></span>
        </div>
      )}
    </section>
  )
}
