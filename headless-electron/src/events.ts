export interface ResizeEvent {
  type: 'resize';
  width: number;
  height: number;
}

export interface BrowseEvent {
  type: 'browse';
  url: string;
}

export interface AcceptEvent {
  type: 'accept';
  imageCount: number;
}