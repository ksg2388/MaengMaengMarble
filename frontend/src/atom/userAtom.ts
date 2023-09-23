import { ResponseAccessTokenType } from '@/types/common/auth.type';
import { DetailUserType } from '@/types/common/common.type';
import { atom } from 'recoil';

export const accessTokenState = atom<ResponseAccessTokenType | null>({
  key: 'accessToken', // unique ID (with respect to other atoms/selectors)
  default: null, // default value (aka initial value)
});

export const userState = atom<DetailUserType | null>({
  key: 'user',
  default: null,
});
