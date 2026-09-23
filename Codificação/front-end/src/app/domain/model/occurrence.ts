export interface OccurrenceMedia {
    url:                 string;
    cloudinaryPublicId?: string;
    imageBlurred?:       boolean;
}

export interface OccurrenceHistory {
    oldStatus?:     string;
    newStatus:      string;
    observation?:   string;
    changedByName?: string;
    changedAt?:     string;
}

export interface Occurrence {
    id?:             number;
    protocolNumber?: string;
    title?:          string;
    description:     string;
    street?:            string;
    number?:            string;
    neighborhood?:      string;
    addressReference?:  string;
    city:               string;
    state?:             string;
    latitude?:       number;
    longitude?:      number;
    urlMedia?:       string;
    imageBlurred?:   boolean;
    media?:          OccurrenceMedia[];
    status:          string;
    type?:           string;
    priority?:       string;
    anonymous?:      boolean;
    supportCount?:   number;
    email?:          string | null;
    fullname?:       string | null;
    trackingCode?:   string;
    createdAt?:      string;
    updatedAt?:      string;
}
